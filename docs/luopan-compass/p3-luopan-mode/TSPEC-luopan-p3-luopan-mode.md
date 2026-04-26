# TSPEC-luopan-p3-luopan-mode
## Technical Specification: Phase 3 — Luopan Display Mode

| Field | Value |
|-------|-------|
| **Version** | 1.0 |
| **Date** | 2026-04-25 |
| **Status** | Draft |
| **Linked REQ** | [REQ-luopan-p3-luopan-mode v0.3-draft](REQ-luopan-p3-luopan-mode.md) |
| **Linked FSPEC** | [FSPEC-luopan-p3-luopan-mode v1.1](FSPEC-luopan-p3-luopan-mode.md) |
| **Author** | Engineering |

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Open Issue Resolutions](#2-open-issue-resolutions)
3. [New Files and Modified Files](#3-new-files-and-modified-files)
4. [Domain Logic](#4-domain-logic)
5. [Data Structures](#5-data-structures)
6. [UI Components](#6-ui-components)
7. [Data Persistence](#7-data-persistence)
8. [ViewModel Design](#8-viewmodel-design)
9. [Navigation Architecture](#9-navigation-architecture)
10. [Performance Strategy](#10-performance-strategy)
11. [Error Handling](#11-error-handling)
12. [Test Strategy](#12-test-strategy)
13. [Requirements Traceability](#13-requirements-traceability)

---

## 1. Architecture Overview

### 1.1 Structural Pattern

Phase 3 adopts the same layered clean-architecture pattern established in Phases 1 and 2:

```
UI Layer          LuopanFragment, LuopanView (custom Canvas View)
                  RingVisibilityBottomSheet, NumericReadoutPanel
                  ↓ observes StateFlow
ViewModel Layer   CompassViewModel (scoped to CompassActivity)
                  LuopanStateMapper (domain use case, called by ViewModel)
                  ↓ calls
Domain Layer      SectorLookup, RingLabelProvider, ZuoXiangLock
                  ↓ pure functions / companion objects, no Android deps
Data Layer        SettingsRepository (extended), CompassUiState (extended)
```

### 1.2 Single-Activity Navigation

The app uses a single Activity + NavHostFragment architecture managed by Jetpack Navigation Component. `CompassActivity` remains the sole Activity. A `NavHostFragment` replaces the current full-screen layout. The existing `CompassActivity` content is migrated to `ModernCompassFragment`.

**Navigation graph:** `res/navigation/nav_graph.xml`

| Mode | Fragment | Nav Destination ID |
|------|----------|-------------------|
| Modern Mode | `ModernCompassFragment` | `dest_modern` |
| Luopan Mode | `LuopanFragment` | `dest_luopan` |
| Sighting Mode | `SightingFragment` (Phase 5 stub) | *(deferred)* |

**Mode switcher:** `TabLayout` (Material Design) docked at the bottom of `CompassActivity`, wired via `NavigationUI.setupWithNavController`. The `TabLayout` is declared in `activity_compass.xml`; the `NavHostFragment` fills the space above it.

### 1.3 ViewModel Sharing Contract

`CompassViewModel` is scoped to `CompassActivity` via `activityViewModels()`. Both `ModernCompassFragment` and `LuopanFragment` obtain the same instance. The sensor pipeline runs continuously in the ViewModel regardless of which fragment is active.

`LuopanFragment` adds Luopan-specific UI state (ring visibility, zoom scale, lock state) as additional fields in `CompassViewModel`. No second ViewModel is introduced.

---

## 2. Open Issue Resolutions

### 2.1 Float vs Double for Bearing Values — Decision

**Decision:** `LuopanState` bearing fields (`xiangBearing`, `zuoBearing`) use `Float`. All sector-lookup input parameters use `Float`. Sector boundary constants are `Float` compile-time constants.

**Rationale:**
- Android sensor APIs (`SensorEvent.values[]`) return `Float`. The magnetic heading delivered by `FusionEngine` and stored in `CompassUiState.heading_deg` is `Double` for display precision (formatted to 0.1°), but the conversion to sector index requires only `Float` precision (6° minimum sector width = 1000× larger than `Float` epsilon at compass magnitudes).
- WMM declination from `MagneticFieldModel.getDeclination()` returns `Float`. `CompassUiState.declination_deg` is already `Float`.
- Keeping bearing values in `LuopanState` as `Float` eliminates unnecessary widening and is consistent with the sensor API type system.
- **Exception:** `CompassUiState.heading_deg` remains `Double` because it is displayed to 0.1° precision. `LuopanStateMapper` converts via `.toFloat()` when passing to `SectorLookup`.

**Field type table for `LuopanState`:**

| Field | Type | Reason |
|-------|------|--------|
| `xiangBearing` | `Float?` | Derived from `heading_deg.toFloat()` at lock time; consistent with sensor origin |
| `zuoBearing` | `Float?` | Derived: `(xiangBearing + 180f) % 360f` |
| All sector-lookup inputs | `Float` | Direct from `CompassUiState.heading_deg.toFloat()` |
| `declinationDeg` in `LuopanState` | `Float` | Mirrors `CompassUiState.declination_deg` (already `Float`) |

### 2.2 Declination Sign Convention — Confirmation

The Phase 2 implementation in `NorthTypeEngine.computeHeadingFields` computes:

```kotlin
displayHeading = (magneticHeading + declination + 360.0) % 360.0
```

This confirms the **East-positive** sign convention: a positive `declinationDeg` means the magnetic north is east of True North, so the display heading is *larger* than the magnetic heading.

The `LuopanState.declinationDeg` field is passed through from `CompassUiState.declination_deg` for View-layer display use. No conversion is needed in the View layer. The numerical readout panel uses `CompassUiState.heading_deg` (already the true-north-adjusted bearing) directly; it does NOT re-apply declination.

**Sign convention stated explicitly:** `declinationDeg > 0` means East of True North. `displayBearing = magneticBearing + declinationDeg`. The View never recomputes declination; it only reads `bearingDeg` from `LuopanState` which is already in the display reference frame.

### 2.3 STABILIZING Behavior — AC-29 Clarification

During `OverallConfidence.STABILIZING`:

- The dial **continues to rotate** — `bearingDeg` keeps updating from the sensor fusion pipeline (gyroscope continues providing heading updates even when the magnetometer is being re-evaluated).
- All sector fields (`mountainChar`, `earthlyBranchChar`, `trigramSymbol`, etc.) **continue to compute** from the live `bearingDeg`.
- The **"Lock 向" button is disabled** — per BR-05, STABILIZING is treated the same as POOR for the lock button.
- The **分金 field shows "N/A — calibrate for 分金 precision"** — `fenJinLabel` is `null` in `LuopanState`.
- The **confidence badge** shows "Calibrating..." (amber).

This differs from `SENSOR_ERROR` where the dial is frozen (no new heading updates and sector fields show "—").

### 2.4 `declinationDeg` Exposure in `LuopanState`

`LuopanState` includes `declinationDeg: Float` (mirroring `CompassUiState.declination_deg`). This allows `LuopanFragment` to display the north-type label and format bearing strings without additional ViewModel calls. The View does NOT use `declinationDeg` to convert bearings — conversion has already occurred in `CompassViewModel`/`NorthTypeEngine` before state emission. The field is exposed for informational display only (e.g., showing "True N" vs "Mag N" in the overlay).

---

## 3. New Files and Modified Files

### 3.1 New Source Files

| File | Package | Purpose |
|------|---------|---------|
| `luopan/SectorLookup.kt` | `com.luopan.compass.luopan` | Sector index lookup for all 6 rings; pure object |
| `luopan/RingLabelProvider.kt` | `com.luopan.compass.luopan` | Maps (ring, sectorIndex) → `LabelData`; pure object |
| `luopan/LuopanStateMapper.kt` | `com.luopan.compass.luopan` | Maps `CompassUiState` + session state → `LuopanState` |
| `luopan/ZuoXiangLock.kt` | `com.luopan.compass.luopan` | 坐向 lock computation and state |
| `luopan/LuopanState.kt` | `com.luopan.compass.luopan` | Data class for luopan UI state |
| `ui/LuopanFragment.kt` | `com.luopan.compass.ui` | Fragment hosting dial + readout |
| `ui/LuopanView.kt` | `com.luopan.compass.ui` | Custom Canvas View — six-ring luopan dial |
| `ui/RingVisibilityBottomSheet.kt` | `com.luopan.compass.ui` | BottomSheetDialogFragment for ring visibility |
| `ui/ModernCompassFragment.kt` | `com.luopan.compass.ui` | Existing `CompassActivity` UI migrated to Fragment |

### 3.2 New Layout Files

| File | Purpose |
|------|---------|
| `res/layout/fragment_luopan.xml` | Root layout for `LuopanFragment` |
| `res/layout/fragment_modern_compass.xml` | Root layout for `ModernCompassFragment` (migrated from `activity_compass.xml`) |
| `res/layout/bottom_sheet_ring_visibility.xml` | Ring visibility toggle sheet |

### 3.3 New Resource Files

| File | Purpose |
|------|---------|
| `res/navigation/nav_graph.xml` | Navigation graph: `dest_modern`, `dest_luopan` |
| `res/font/NotoSerifCJK_TC.ttf` (or `.otf`) | CJK font for ring labels |

### 3.4 Modified Files

| File | Change |
|------|--------|
| `ui/CompassActivity.kt` | Add `NavHostFragment`, `TabLayout`; remove direct UI wiring (migrated to `ModernCompassFragment`) |
| `res/layout/activity_compass.xml` | Replace content with `NavHostFragment` + bottom `TabLayout` |
| `ui/CompassUiState.kt` | Add `val luopan: LuopanState` field |
| `ui/CompassViewModel.kt` | Add luopan state computation: session state fields (ring visibility, zoom, lock), call `LuopanStateMapper` |
| `settings/SettingsRepository.kt` | Add `luopan_show_romanization`, `luopan_show_my_language`, `display_mode` keys |

---

## 4. Domain Logic

### 4.1 SectorLookup

**File:** `app/src/main/java/com/luopan/compass/luopan/SectorLookup.kt`

**Design:** Pure `object` (singleton). No Android dependencies. All sector tables are `companion object` compile-time constants.

**Input:** `bearing: Float` in `[0f, 360f)`. The caller normalizes to this range before calling. Values at 360.0f are treated as 0.0f.

**Output:** Sector index (0-based Int) for the requested ring.

**Algorithm — universal sector lookup:**

```
fun normalizedBearing(raw: Float): Float = ((raw % 360f) + 360f) % 360f

fun lookupSector(bearing: Float, sectors: Array<SectorEntry>): Int {
    val b = normalizedBearing(bearing)
    for ((index, entry) in sectors.withIndex()) {
        if (entry.wrapsAround) {
            // Straddles 0°/360°: [start, 360) ∪ [0, end)
            if (b >= entry.startDeg || b < entry.endDeg) return index
        } else {
            if (b >= entry.startDeg && b < entry.endDeg) return index
        }
    }
    // Should never reach here if sectors cover full 360°
    throw IllegalStateException("No sector found for bearing $b")
}
```

**`SectorEntry` data class:**

```kotlin
data class SectorEntry(
    val startDeg: Float,
    val endDeg: Float,
    val wrapsAround: Boolean = false  // true only for sectors straddling 0°/360°
)
```

**Sector tables (compile-time constants in `companion object`):**

Each ring's sectors are defined as a `val` array in the companion object. Wrap-around sectors are marked with `wrapsAround = true`.

*Ring 2 — 先天八卦 (8 sectors, 45°/sector):*

| Index | Sector | Start | End | Wrap |
|-------|--------|-------|-----|------|
| 0 | ☴ 巽 北 | 337.5f | 22.5f | true |
| 1 | ☳ 震 東北 | 22.5f | 67.5f | false |
| 2 | ☲ 離 東 | 67.5f | 112.5f | false |
| 3 | ☱ 兌 東南 | 112.5f | 157.5f | false |
| 4 | ☰ 乾 南 | 157.5f | 202.5f | false |
| 5 | ☷ 坤 西南 | 202.5f | 247.5f | false |
| 6 | ☶ 艮 西 | 247.5f | 292.5f | false |
| 7 | ☵ 坎 西北 | 292.5f | 337.5f | false |

*Ring 3 — 後天八卦 (8 sectors, 45°/sector):*

| Index | Sector | Start | End | Wrap |
|-------|--------|-------|-----|------|
| 0 | ☵ 坎 北 | 337.5f | 22.5f | true |
| 1 | ☶ 艮 東北 | 22.5f | 67.5f | false |
| 2 | ☳ 震 東 | 67.5f | 112.5f | false |
| 3 | ☴ 巽 東南 | 112.5f | 157.5f | false |
| 4 | ☲ 離 南 | 157.5f | 202.5f | false |
| 5 | ☷ 坤 西南 | 202.5f | 247.5f | false |
| 6 | ☱ 兌 西 | 247.5f | 292.5f | false |
| 7 | ☰ 乾 西北 | 292.5f | 337.5f | false |

*Ring 4 — 十二地支 (12 sectors, 30°/sector):*

| Index | 地支 | Start | End | Wrap |
|-------|------|-------|-----|------|
| 0 | 子 | 345.0f | 15.0f | true |
| 1 | 丑 | 15.0f | 45.0f | false |
| 2 | 寅 | 45.0f | 75.0f | false |
| 3 | 卯 | 75.0f | 105.0f | false |
| 4 | 辰 | 105.0f | 135.0f | false |
| 5 | 巳 | 135.0f | 165.0f | false |
| 6 | 午 | 165.0f | 195.0f | false |
| 7 | 未 | 195.0f | 225.0f | false |
| 8 | 申 | 225.0f | 255.0f | false |
| 9 | 酉 | 255.0f | 285.0f | false |
| 10 | 戌 | 285.0f | 315.0f | false |
| 11 | 亥 | 315.0f | 345.0f | false |

*Ring 5 — 二十四山 (24 sectors, 15°/sector):*

| Index | 山 | Start | End | Wrap |
|-------|---|-------|-----|------|
| 0 | 壬 | 337.5f | 352.5f | false |
| 1 | 子 | 352.5f | 7.5f | true |
| 2 | 癸 | 7.5f | 22.5f | false |
| 3 | 丑 | 22.5f | 37.5f | false |
| 4 | 艮 | 37.5f | 52.5f | false |
| 5 | 寅 | 52.5f | 67.5f | false |
| 6 | 甲 | 67.5f | 82.5f | false |
| 7 | 卯 | 82.5f | 97.5f | false |
| 8 | 乙 | 97.5f | 112.5f | false |
| 9 | 辰 | 112.5f | 127.5f | false |
| 10 | 巽 | 127.5f | 142.5f | false |
| 11 | 巳 | 142.5f | 157.5f | false |
| 12 | 丙 | 157.5f | 172.5f | false |
| 13 | 午 | 172.5f | 187.5f | false |
| 14 | 丁 | 187.5f | 202.5f | false |
| 15 | 未 | 202.5f | 217.5f | false |
| 16 | 坤 | 217.5f | 232.5f | false |
| 17 | 申 | 232.5f | 247.5f | false |
| 18 | 庚 | 247.5f | 262.5f | false |
| 19 | 酉 | 262.5f | 277.5f | false |
| 20 | 辛 | 277.5f | 292.5f | false |
| 21 | 戌 | 292.5f | 307.5f | false |
| 22 | 乾 | 307.5f | 322.5f | false |
| 23 | 亥 | 322.5f | 337.5f | false |

*Ring 6 — 六十分金 (60 sectors, 6°/sector):*

All 60 entries from FSPEC §4.6 encoded as `SectorEntry` array. The wrap-around sector is:

| Index | Label | Start | End | Wrap |
|-------|-------|-------|-----|------|
| 1 | 壬子分金 | 358.0f | 4.0f | true |

All other Ring 6 sectors use standard `[start, end)` with no wrap. Sector #0 (庚子分金) has range `[352f, 358f)` with no wrap.

**Public API:**

```kotlin
object SectorLookup {
    fun ring2(bearing: Float): Int  // 先天八卦 sector index 0–7
    fun ring3(bearing: Float): Int  // 後天八卦 sector index 0–7
    fun ring4(bearing: Float): Int  // 十二地支 sector index 0–11
    fun ring5(bearing: Float): Int  // 二十四山 sector index 0–23
    fun ring6(bearing: Float): Int  // 六十分金 sector index 0–59
}
```

### 4.2 RingLabelProvider

**File:** `app/src/main/java/com/luopan/compass/luopan/RingLabelProvider.kt`

**Design:** Pure `object`. All data as compile-time `val` arrays keyed by sector index. No Android dependencies.

**`LabelData` data class:**

```kotlin
data class LabelData(
    val character: String,       // Primary zh-Hant character(s), e.g. "午", "☲ 離 南"
    val pinyin: String,          // Pinyin romanization, e.g. "Wǔ", "Lí · Nán"
    val english: String          // English equivalent per §5.8; empty string if none
)
```

**Public API:**

```kotlin
object RingLabelProvider {
    fun ring2Label(sectorIndex: Int): LabelData   // 先天八卦
    fun ring3Label(sectorIndex: Int): LabelData   // 後天八卦
    fun ring4Label(sectorIndex: Int): LabelData   // 十二地支
    fun ring5Label(sectorIndex: Int): LabelData   // 二十四山
    fun ring6Label(sectorIndex: Int): LabelData   // 六十分金
}
```

**Data completeness:** All 8 + 8 + 12 + 24 + 60 entries populated from FSPEC §4.2–4.6 and REQ §5.3a, §5.6, §5.8. The 60-entry Ring 6 array is a compile-time constant using the full table from FSPEC §4.6.

### 4.3 LuopanStateMapper

**File:** `app/src/main/java/com/luopan/compass/luopan/LuopanStateMapper.kt`

**Design:** Pure `object` with a single `map()` function. No Android dependencies. Called by `CompassViewModel` in the sensor collection coroutine (`Dispatchers.Default`).

**Signature:**

```kotlin
object LuopanStateMapper {
    fun map(
        compassState: CompassUiState,
        lockState: ZuoXiangLock.LockState,
        showRomanization: Boolean,
        showMyLanguage: Boolean
    ): LuopanState
}
```

**Logic:**

1. Extract `bearingDeg: Float = compassState.heading_deg.toFloat()`.
2. Normalize: `b = ((bearingDeg % 360f) + 360f) % 360f`.
3. Evaluate Ring 5 sector: `ring5Idx = SectorLookup.ring5(b)` → `ring5Label = RingLabelProvider.ring5Label(ring5Idx)`.
4. Evaluate Ring 4 sector: `ring4Idx = SectorLookup.ring4(b)` → `ring4Label = RingLabelProvider.ring4Label(ring4Idx)`.
5. Evaluate Ring 3 sector: `ring3Idx = SectorLookup.ring3(b)` → `ring3Label = RingLabelProvider.ring3Label(ring3Idx)`.
6. Evaluate Ring 6 only when `compassState.confidence == OverallConfidence.HIGH`: `ring6Idx = SectorLookup.ring6(b)` → `ring6Label = RingLabelProvider.ring6Label(ring6Idx)`.
7. Apply localization: if `showMyLanguage`, use `LabelData.english` where non-empty; otherwise `LabelData.character`.
8. Apply romanization: if `showRomanization`, include `LabelData.pinyin`.
9. For `SENSOR_ERROR`: set all character fields to `"—"` (em dash), `fenJinLabel = null`.
10. Map lock state from `lockState` into `LuopanState`.
11. Return completed `LuopanState`.

**SENSOR_ERROR guard:** When `compassState.confidence == SENSOR_ERROR`, character fields are forced to `"—"` regardless of bearing. Sector lookups are still performed (cheap, no side effects) but results are discarded.

### 4.4 ZuoXiangLock

**File:** `app/src/main/java/com/luopan/compass/luopan/ZuoXiangLock.kt`

**Design:** Simple class holding lock state. Lives inside `CompassViewModel` as a private field. Thread-safe: only mutated on `Dispatchers.Default` in sensor collection or on main thread via explicit ViewModel functions.

```kotlin
class ZuoXiangLock {

    data class LockState(
        val isLockActive: Boolean = false,
        val xiangBearing: Float? = null,   // True/Mag N Float degrees
        val xiangMountain: String? = null, // Ring 5 山 char, e.g. "艮"
        val zuoBearing: Float? = null,
        val zuoMountain: String? = null
    )

    private var _lockState = LockState()
    val lockState: LockState get() = _lockState

    /** Lock 向 at the given bearing. Derives 坐 and looks up both 山 labels. */
    fun lock(bearing: Float) {
        val xiang = ((bearing % 360f) + 360f) % 360f
        val zuo = (xiang + 180f) % 360f
        val xiangMtn = RingLabelProvider.ring5Label(SectorLookup.ring5(xiang)).character
        val zuoMtn = RingLabelProvider.ring5Label(SectorLookup.ring5(zuo)).character
        _lockState = LockState(
            isLockActive = true,
            xiangBearing = xiang,
            xiangMountain = xiangMtn,
            zuoBearing = zuo,
            zuoMountain = zuoMtn
        )
    }

    /** Clear the lock. */
    fun clear() {
        _lockState = LockState()
    }

    /** Re-derive 山 labels when north type changes. Called from ViewModel on northType change. */
    fun rederive(newBearing: Float) {
        if (!_lockState.isLockActive) return
        lock(newBearing)
    }
}
```

**坐 formula:** `zuoBearing = (xiangBearing + 180f) % 360f`. This is guaranteed to produce a value in `[0f, 360f)` because `xiangBearing` is in `[0f, 360f)`, so `xiangBearing + 180f` is in `[180f, 540f)` and modulo 360 maps it to `[0f, 180f)` when `xiang >= 180` and `[180f, 360f)` when `xiang < 180`.

---

## 5. Data Structures

### 5.1 LuopanState Data Class

**File:** `app/src/main/java/com/luopan/compass/luopan/LuopanState.kt`

```kotlin
package com.luopan.compass.luopan

import com.luopan.compass.model.OverallConfidence

/**
 * Luopan-mode UI state. Emitted by CompassViewModel as part of CompassUiState.
 *
 * TYPE DECISIONS:
 * - Bearing fields (xiangBearing, zuoBearing): Float — consistent with Android sensor API
 *   which returns Float. WMM declination is also Float. The sector widths (6°–45°) are
 *   far larger than Float epsilon, making Float precision adequate.
 * - declinationDeg: Float — mirrors CompassUiState.declination_deg (already Float from WMM).
 *   Exposed for informational display in the View only; not used for bearing conversion.
 *
 * SENSOR_ERROR state: character fields are "—"; fenJinLabel is null.
 * STABILIZING state: character fields continue updating; fenJinLabel is null.
 */
data class LuopanState(

    // ---- Ring 5 — 二十四山 ----
    val mountainChar: String,          // e.g. "午" (or "—" on SENSOR_ERROR, or English if showMyLanguage)
    val mountainPinyin: String,        // e.g. "Wǔ" (empty when !showRomanization)

    // ---- Ring 4 — 十二地支 ----
    val earthlyBranchChar: String,     // e.g. "午"
    val earthlyBranchPinyin: String,

    // ---- Ring 3 — 後天八卦 ----
    val trigramSymbol: String,         // e.g. "☲" (always retained regardless of language mode)
    val trigramName: String,           // e.g. "離" (or English "Li" if showMyLanguage)
    val trigramDirection: String,      // e.g. "南" (or English "South" if showMyLanguage)

    // ---- Ring 6 — 六十分金 ----
    val fenJinLabel: String?,          // e.g. "壬午分金"; null when confidence != HIGH

    // ---- Current bearing (for readout panel display) ----
    val bearingDeg: Float,             // Display bearing from CompassUiState.heading_deg.toFloat()
    val northLabel: String,            // "True N" or "Mag N" — from CompassUiState.north_label
    val declinationDeg: Float,         // From CompassUiState.declination_deg; informational only

    // ---- 坐向 lock state ----
    val isLockActive: Boolean,
    val xiangBearing: Float?,          // null = unlocked
    val xiangMountain: String?,        // Ring 5 山 for 向 (null = unlocked)
    val zuoBearing: Float?,            // (xiangBearing + 180f) % 360f; null = unlocked
    val zuoMountain: String?,          // Ring 5 山 for 坐 (null = unlocked)

    // ---- Confidence (mirrors CompassUiState.confidence) ----
    val confidence: OverallConfidence  // HIGH | MODERATE | POOR | STABILIZING | SENSOR_ERROR
) {
    companion object {
        val INITIAL = LuopanState(
            mountainChar = "—",
            mountainPinyin = "",
            earthlyBranchChar = "—",
            earthlyBranchPinyin = "",
            trigramSymbol = "—",
            trigramName = "—",
            trigramDirection = "—",
            fenJinLabel = null,
            bearingDeg = 0f,
            northLabel = "Magnetic N",
            declinationDeg = 0f,
            isLockActive = false,
            xiangBearing = null,
            xiangMountain = null,
            zuoBearing = null,
            zuoMountain = null,
            confidence = OverallConfidence.POOR
        )
    }
}
```

### 5.2 CompassUiState Extension

`CompassUiState` gains a new field:

```kotlin
val luopan: LuopanState = LuopanState.INITIAL
```

The `INITIAL` companion object of `CompassUiState` is updated to include `luopan = LuopanState.INITIAL`.

### 5.3 Session State in CompassViewModel

The following session-only state fields are added directly to `CompassViewModel`. They are NOT in `LuopanState` (which is a pure output data class) — they are inputs to `LuopanStateMapper`:

```kotlin
// Ring visibility — session-only, default all true
private val ringVisible = MutableStateFlow(BooleanArray(6) { true })  // index 0 = Ring 1

// Zoom scale — session-only, default 1.0f
private val _zoomScale = MutableStateFlow(1.0f)
val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

// ZuoXiang lock — session-only
private val zuoXiangLock = ZuoXiangLock()

// Localization settings — read from SettingsRepository at startup, updated on change
private var showRomanization: Boolean = false
private var showMyLanguage: Boolean = false
```

**Ring visibility exposure:**

```kotlin
val ringVisibility: StateFlow<BooleanArray> = ringVisible.asStateFlow()

fun setRingVisible(ringIndex: Int, visible: Boolean) {
    // ringIndex: 0-based (Ring 1 = 0, Ring 6 = 5)
    val arr = ringVisible.value.clone()
    arr[ringIndex] = visible
    ringVisible.value = arr
}
```

**Zoom scale update:**

```kotlin
fun setZoomScale(scale: Float) {
    _zoomScale.value = scale.coerceIn(0.8f, 2.0f)
}
```

**Lock functions:**

```kotlin
fun lockXiang() {
    val currentBearing = uiState.value.heading_deg.toFloat()
    val confidence = uiState.value.confidence
    if (confidence == OverallConfidence.HIGH || confidence == OverallConfidence.MODERATE) {
        zuoXiangLock.lock(currentBearing)
        recomputeLuopanState()
    }
}

fun clearXiang() {
    zuoXiangLock.clear()
    recomputeLuopanState()
}
```

---

## 6. UI Components

### 6.1 LuopanView (Custom Canvas View)

**File:** `app/src/main/java/com/luopan/compass/ui/LuopanView.kt`

**Base class:** `android.view.View`

**Hardware acceleration:** `setLayerType(LAYER_TYPE_HARDWARE, null)` called in `init {}` block. This ensures the Canvas is hardware-accelerated, meeting REQ-NFR-LUOPAN-02.

**Constructor:**

```kotlin
class LuopanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr)
```

#### 6.1.1 Geometry Pre-computation (onSizeChanged)

All ring geometry is pre-computed in `onSizeChanged` and cached as instance fields. `onDraw` only reads cached values.

```kotlin
// Cached geometry — computed in onSizeChanged, read in onDraw
private var cx = 0f
private var cy = 0f
private var baseRadius = 0f

// Ring outer radii as fractions of baseRadius (Ring 1 innermost, Ring 6 outermost)
// Fractions designed for legibility: Ring 6 labels ≥ 8sp on 5-inch 1080p
private val RING_RADIUS_FRACTIONS = floatArrayOf(
    0.12f,  // Ring 1 — 天池 needle circle
    0.30f,  // Ring 2 — 先天八卦 outer edge
    0.48f,  // Ring 3 — 後天八卦 outer edge
    0.62f,  // Ring 4 — 十二地支 outer edge
    0.78f,  // Ring 5 — 二十四山 outer edge
    1.00f   // Ring 6 — 六十分金 outer edge
)

// Pre-computed sector label positions for each ring (angle, radius for text placement)
// Populated in onSizeChanged: one entry per sector per ring
private lateinit var ring2LabelPositions: Array<PointF>  // 8 entries
private lateinit var ring3LabelPositions: Array<PointF>  // 8 entries
private lateinit var ring4LabelPositions: Array<PointF>  // 12 entries
private lateinit var ring5LabelPositions: Array<PointF>  // 24 entries
private lateinit var ring6LabelPositions: Array<PointF>  // 60 entries

// Pre-computed arc paths for ring borders
private lateinit var ringBorderPaths: Array<Path>  // 6 entries
```

Label positions are computed in `onSizeChanged`:

```kotlin
override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    cx = w / 2f
    cy = h / 2f
    baseRadius = min(cx, cy) * zoomScale

    // Pre-compute label positions for each ring
    ring2LabelPositions = computeLabelPositions(8, 45f, RING_RADIUS_FRACTIONS[1], RING_RADIUS_FRACTIONS[2])
    ring3LabelPositions = computeLabelPositions(8, 45f, RING_RADIUS_FRACTIONS[2], RING_RADIUS_FRACTIONS[3])
    // ... etc.
}

private fun computeLabelPositions(
    count: Int,
    degreesPerSector: Float,
    innerFraction: Float,
    outerFraction: Float
): Array<PointF> {
    val midFraction = (innerFraction + outerFraction) / 2f
    val midRadius = baseRadius * midFraction
    return Array(count) { i ->
        val angleDeg = i * degreesPerSector + degreesPerSector / 2f  // center of sector
        val angleRad = Math.toRadians(angleDeg.toDouble())
        PointF(
            cx + midRadius * sin(angleRad).toFloat(),
            cy - midRadius * cos(angleRad).toFloat()
        )
    }
}
```

#### 6.1.2 onDraw Implementation

`onDraw` performs: save canvas → apply rotation → draw ring assembly → restore → draw fixed pointer overlay.

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // Apply zoom scale (centered on dial center)
    canvas.save()
    canvas.scale(zoomScale, zoomScale, cx, cy)

    // Rotate the entire ring assembly by -bearingDeg (counter-clockwise relative to device heading)
    canvas.save()
    canvas.rotate(-bearingDeg, cx, cy)

    drawBackground(canvas)
    drawRingBorders(canvas)
    if (ringVisible[0]) drawRing1Needle(canvas)
    if (ringVisible[1]) drawRing2Labels(canvas)
    if (ringVisible[2]) drawRing3Labels(canvas)
    if (ringVisible[3]) drawRing4Labels(canvas)
    if (ringVisible[4]) drawRing5Labels(canvas)
    if (ringVisible[5]) drawRing6Labels(canvas)
    if (isLockActive) drawGoldTickMark(canvas)

    canvas.restore()  // undo rotation

    // Fixed pointer — drawn OUTSIDE the rotation transform
    drawFixedPointer(canvas)

    canvas.restore()  // undo zoom scale
}
```

**Rotation formula:** `dialRotationDeg = -bearingDeg`. When device heading is 180° (pointing South), the dial rotates -180° so that the "南" sector appears at the top (under the pointer). This is the standard luopan counter-rotation behavior.

#### 6.1.3 Ring Label Rendering

Each ring's `drawRingNLabels` method reads pre-computed label positions and draws text. Rotation has already been applied to the canvas, so label positions rotate with the ring.

For Ring 4 (地支) and Ring 5 (山): single CJK character per sector, rotated 90° relative to sector center radius (radial text). For Ring 3 (後天八卦): trigram + 卦名 + 方位 stacked or combined. For Ring 6 (分金): 2-char label, minimum 8sp.

**Font:** Noto Serif CJK TC loaded once in the companion object:

```kotlin
companion object {
    private lateinit var cjkTypeface: Typeface

    fun initTypeface(context: Context) {
        if (!::cjkTypeface.isInitialized) {
            cjkTypeface = ResourcesCompat.getFont(context, R.font.noto_serif_cjk_tc)
                ?: Typeface.DEFAULT
        }
    }
}
```

`initTypeface` is called from `LuopanFragment.onViewCreated()`.

#### 6.1.4 Gold Tick Mark

When `isLockActive` is true, a gold tick mark is drawn at `xiangBearing` degrees within the rotation-transformed canvas. Since it is drawn inside the rotation transform, it rotates with the dial (it appears at the correct physical bearing position as the device rotates).

```kotlin
private fun drawGoldTickMark(canvas: Canvas) {
    val tickBearing = xiangBearing ?: return
    val tickAngleRad = Math.toRadians(tickBearing.toDouble())
    val outerR = baseRadius * RING_RADIUS_FRACTIONS[4]  // outer edge of Ring 5
    val innerR = outerR * 0.92f
    canvas.drawLine(
        cx + innerR * sin(tickAngleRad).toFloat(),
        cy - innerR * cos(tickAngleRad).toFloat(),
        cx + outerR * sin(tickAngleRad).toFloat(),
        cy - outerR * cos(tickAngleRad).toFloat(),
        goldTickPaint  // stroke width 4dp, color #C9A84C
    )
}
```

#### 6.1.5 Pinch-to-Zoom Gesture

`LuopanView` attaches a `ScaleGestureDetector` in its init block:

```kotlin
private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val newScale = (zoomScale * detector.scaleFactor).coerceIn(0.8f, 2.0f)
        onZoomChanged?.invoke(newScale)
        return true
    }
})
```

`onZoomChanged` is a lambda set by `LuopanFragment`: it calls `viewModel.setZoomScale(newScale)`. The ViewModel emits an updated `zoomScale` StateFlow; `LuopanFragment` passes the new value to `LuopanView.setZoomScale(scale)`.

#### 6.1.6 Long-Press for Ring Visibility

`LuopanView` attaches a `GestureDetector` to detect long-press (≥ 500 ms):

```kotlin
private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onLongPress(e: MotionEvent) {
        onLongPressDetected?.invoke()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
})
```

`onLongPressDetected` is a lambda set by `LuopanFragment` to show `RingVisibilityBottomSheet`.

#### 6.1.7 Public Setter API

```kotlin
fun setBearingDeg(deg: Float)
fun setZoomScale(scale: Float)
fun setRingVisible(index: Int, visible: Boolean)  // 0-based, Ring 1 = 0
fun setLockState(active: Boolean, xiangBearing: Float?)
```

Each setter stores the value and calls `invalidate()`.

### 6.2 LuopanFragment

**File:** `app/src/main/java/com/luopan/compass/ui/LuopanFragment.kt`

**Layout:** `res/layout/fragment_luopan.xml`

**ViewModel:** `val viewModel: CompassViewModel by activityViewModels()`

**Fragment lifecycle responsibilities:**
- `onViewCreated`: call `LuopanView.initTypeface(requireContext())`, wire gesture callbacks, set up observers.
- `onStart` / `onStop`: no sensor management (ViewModel handles sensors).

**Observers (all in `viewLifecycleOwner.lifecycleScope`):**

```kotlin
// Main state observer
viewModel.uiState.collect { state ->
    val luopan = state.luopan
    luopanView.setBearingDeg(luopan.bearingDeg)
    luopanView.setLockState(luopan.isLockActive, luopan.xiangBearing)
    updateNumericReadout(luopan)
    updateLockButton(luopan)
    updateZuoXiangOverlay(luopan)
}

// Ring visibility observer
viewModel.ringVisibility.collect { visible ->
    for (i in 0 until 6) luopanView.setRingVisible(i, visible[i])
}

// Zoom scale observer
viewModel.zoomScale.collect { scale ->
    luopanView.setZoomScale(scale)
    luopanView.requestLayout()  // trigger onSizeChanged for geometry recompute
}
```

**Numeric readout panel update:**

```kotlin
private fun updateNumericReadout(state: LuopanState) {
    tvMountain.text = formatMountainField(state)
    tvEarthlyBranch.text = formatEarthlyBranchField(state)
    tvTrigram.text = formatTrigramField(state)
    tvBearing.text = "%.1f°".format(state.bearingDeg)
    tvNorthType.text = formatNorthLabel(state.northLabel)
    tvFenJin.text = state.fenJinLabel ?: getString(R.string.fen_jin_na)
    tvConfidence.text = formatConfidenceBadge(state.confidence)
    tvConfidence.setBackgroundColor(confidenceColor(state.confidence))
}
```

**Lock button behavior:**

```kotlin
private fun updateLockButton(state: LuopanState) {
    val canLock = state.confidence == OverallConfidence.HIGH ||
                  state.confidence == OverallConfidence.MODERATE
    btnLockXiang.isEnabled = canLock && !state.isLockActive
    btnLockXiang.text = if (state.isLockActive)
        getString(R.string.clear_xiang) else getString(R.string.lock_xiang)

    if (!canLock && !state.isLockActive) {
        // Show tooltip on disabled tap via OnClickListener
        btnLockXiang.setOnClickListener {
            Toast.makeText(requireContext(),
                R.string.cannot_lock_heading_unreliable, Toast.LENGTH_SHORT).show()
        }
    } else {
        btnLockXiang.setOnClickListener {
            if (state.isLockActive) viewModel.clearXiang()
            else viewModel.lockXiang()
        }
    }
}
```

### 6.3 Fragment Layout — fragment_luopan.xml

Structure (ConstraintLayout root):

```
ConstraintLayout (background: #2C0E0E)
├── LuopanView (id: luopanView, top half, square 1:1 ratio)
├── NumericReadoutPanel (id: readoutPanel, below dial)
│   ├── TextView mountain (山 char + pinyin)
│   ├── TextView earthlyBranch (地支 char + pinyin)
│   ├── TextView trigram (☲ 離 南)
│   ├── TextView bearing (180.0°)
│   ├── TextView northType (True N)
│   ├── TextView fenJin (壬午分金 or N/A text)
│   └── TextView confidenceBadge
├── CardView zuoXiangOverlay (id: zuoXiangOverlay, visibility=GONE initially)
│   ├── TextView xiangLabel ("向: 艮 (45.0° True N)")
│   └── TextView zuoLabel ("坐: 坤 (225.0° True N)")
├── Button btnLockXiang (id: btnLockXiang, "Lock 向")
└── FrameLayout pointerContainer (fixed pointer, positioned over luopanView center-top)
    └── View pointer (gold triangle, static, not subject to dial rotation)
```

### 6.4 RingVisibilityBottomSheet

**File:** `app/src/main/java/com/luopan/compass/ui/RingVisibilityBottomSheet.kt`

**Base class:** `BottomSheetDialogFragment`

**Layout:** `res/layout/bottom_sheet_ring_visibility.xml`

The sheet contains a `LinearLayout` with 6 rows. Each row has a `Switch` control. Changes are propagated immediately via a callback:

```kotlin
var onRingVisibilityChanged: ((ringIndex: Int, visible: Boolean) -> Unit)? = null
```

`LuopanFragment` sets this callback to call `viewModel.setRingVisible(ringIndex, visible)`.

**Accessibility alternative:** A three-dot overflow menu `MenuItem` in the Luopan Mode action bar also opens this sheet, providing a TalkBack-accessible alternative to the long-press gesture.

### 6.5 ModernCompassFragment

**File:** `app/src/main/java/com/luopan/compass/ui/ModernCompassFragment.kt`

This is the existing `CompassActivity` UI logic migrated to a `Fragment`. The layout `fragment_modern_compass.xml` is a copy of the current `activity_compass.xml` content. The fragment observes `viewModel.uiState` for the `CompassUiState` fields it needs (all existing Phase 1 and Phase 2 fields). It does NOT observe `LuopanState`.

---

## 7. Data Persistence

### 7.1 SettingsRepository Additions

**File:** `app/src/main/java/com/luopan/compass/settings/SettingsRepository.kt`

Three new persisted keys:

```kotlin
companion object {
    // Existing keys ...

    // Phase 3 additions
    const val KEY_DISPLAY_MODE         = "display_mode"
    const val KEY_LUOPAN_ROMANIZATION  = "luopan_show_romanization"
    const val KEY_LUOPAN_MY_LANGUAGE   = "luopan_show_my_language"

    const val DISPLAY_MODE_MODERN  = "MODERN"
    const val DISPLAY_MODE_LUOPAN  = "LUOPAN"
}

var displayMode: String
    get() = prefs.getString(KEY_DISPLAY_MODE, DISPLAY_MODE_MODERN) ?: DISPLAY_MODE_MODERN
    set(value) = prefs.edit { putString(KEY_DISPLAY_MODE, value) }

var luopanShowRomanization: Boolean
    get() = prefs.getBoolean(KEY_LUOPAN_ROMANIZATION, false)
    set(value) = prefs.edit { putBoolean(KEY_LUOPAN_ROMANIZATION, value) }

var luopanShowMyLanguage: Boolean
    get() = prefs.getBoolean(KEY_LUOPAN_MY_LANGUAGE, false)
    set(value) = prefs.edit { putBoolean(KEY_LUOPAN_MY_LANGUAGE, value) }
```

**File:** `luopan_settings` SharedPreferences file (same as existing `SettingsRepository` — no new file needed).

### 7.2 Session-Only State (Not Persisted)

The following MUST NOT be written to `SettingsRepository` (REQ §5.7):

| ViewModel field | Type | Default | Reset on |
|----------------|------|---------|----------|
| `ringVisible` (indices 0–5) | `BooleanArray` | `[true × 6]` | Cold start |
| `_zoomScale` | `Float` | `1.0f` | Cold start |
| `zuoXiangLock` (lock state) | `ZuoXiangLock.LockState` | unlocked | Cold start or explicit "Clear 向" |

These survive configuration changes because they live in `CompassViewModel` (which survives configuration changes via `ViewModelProvider`).

---

## 8. ViewModel Design

### 8.1 LuopanState Computation Point

`LuopanStateMapper.map()` is called inside `startSensorCollection()` on `Dispatchers.Default`, directly after `buildUiState()` computes the base `CompassUiState`. The result is attached to `CompassUiState.luopan` before emission.

```kotlin
// Inside startSensorCollection, after existing buildUiState call:
val luopanState = LuopanStateMapper.map(
    compassState = uiState,
    lockState = zuoXiangLock.lockState,
    showRomanization = showRomanization,
    showMyLanguage = showMyLanguage
)
val uiStateWithLuopan = uiState.copy(luopan = luopanState)
_uiState.value = uiStateWithLuopan
```

This ensures sector-lookup computation happens on `Dispatchers.Default` (sensor pipeline thread), not on the main thread.

### 8.2 North Type Change → Lock Re-derivation

When `northType` changes, the locked bearing must re-derive. Since the sensor pipeline recomputes `heading_deg` on every frame, and `lockXiang()` records the bearing at the moment of lock, a north type change produces a new `heading_deg` on the next frame. The lock re-derivation is implemented by re-running `LuopanStateMapper.map()` with the updated `CompassUiState` — the mapper reads `compassState.heading_deg.toFloat()` as the input for lock re-derivation when `isLockActive == true`.

The `ZuoXiangLock` is updated in `recomputeLuopanState()` after a north type change:

```kotlin
fun onNorthTypeChanged() {
    val currentBearing = uiState.value.heading_deg.toFloat()
    zuoXiangLock.rederive(currentBearing)
}
```

This is called from the `northType` StateFlow observer inside `CompassViewModel.init {}`.

### 8.3 Localization Settings Read

`showRomanization` and `showMyLanguage` are read from `SettingsRepository` in `CompassViewModel.init {}` and stored as `private var`. When the user changes them via Settings, the change is written to `SettingsRepository` by the UI layer and the ViewModel is notified via a `fun setShowRomanization(v: Boolean)` / `fun setShowMyLanguage(v: Boolean)` method that updates the local field and triggers `recomputeLuopanState()`.

### 8.4 Mode Persistence

When `LuopanFragment` becomes active (`onStart`), it calls `viewModel.setDisplayMode(DisplayMode.LUOPAN)`. When `ModernCompassFragment` becomes active, it calls `viewModel.setDisplayMode(DisplayMode.MODERN)`. `setDisplayMode` writes to `SettingsRepository.displayMode`. On cold start, `CompassActivity` reads `SettingsRepository.displayMode` and navigates to the appropriate fragment via `navController.navigate(...)`.

---

## 9. Navigation Architecture

### 9.1 Activity Layout

`activity_compass.xml` is restructured:

```xml
<LinearLayout orientation="vertical">
    <FragmentContainerView
        android:id="@+id/nav_host_fragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        app:navGraph="@navigation/nav_graph"
        app:defaultNavHost="true"
        android:layout_weight="1" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        android:layout_height="56dp" />
</LinearLayout>
```

### 9.2 Navigation Graph

`res/navigation/nav_graph.xml`:

```xml
<navigation
    android:id="@+id/nav_graph"
    app:startDestination="@id/dest_modern">

    <fragment
        android:id="@+id/dest_modern"
        android:name="com.luopan.compass.ui.ModernCompassFragment"
        android:label="Modern" />

    <fragment
        android:id="@+id/dest_luopan"
        android:name="com.luopan.compass.ui.LuopanFragment"
        android:label="Luopan" />
</navigation>
```

### 9.3 Tab ↔ NavController Wiring

In `CompassActivity.onCreate`:

```kotlin
val navController = navHostFragment.navController
tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
    override fun onTabSelected(tab: TabLayout.Tab) {
        when (tab.position) {
            0 -> navController.navigate(R.id.dest_modern)
            1 -> navController.navigate(R.id.dest_luopan)
        }
    }
    // ...
})
navController.addOnDestinationChangedListener { _, destination, _ ->
    when (destination.id) {
        R.id.dest_modern -> tabLayout.selectTab(tabLayout.getTabAt(0))
        R.id.dest_luopan -> tabLayout.selectTab(tabLayout.getTabAt(1))
    }
}
```

### 9.4 Transition Budget

Mode transitions MUST complete within 300 ms. The `LuopanFragment` background is set to `#2C0E0E` in its layout, so the first visible frame on entry is the dark background while `LuopanView` renders its first complete frame. No loading spinner is shown.

---

## 10. Performance Strategy

### 10.1 onDraw Budget — 16 ms

To meet REQ-NFR-LUOPAN-02:

1. **Pre-compute geometry in `onSizeChanged`:** All ring radii, sector center angles, label positions, and arc paths are computed once and cached. `onDraw` only reads these cached values — no trigonometric computation in `onDraw`.

2. **Hardware acceleration:** `setLayerType(LAYER_TYPE_HARDWARE, null)` — enables GPU-backed Canvas drawing. The rotation transform (`canvas.rotate`) is GPU-accelerated.

3. **Paint objects allocated once:** All `Paint` objects are instance fields initialized in `init {}` with `Paint.ANTI_ALIAS_FLAG`. No `new Paint()` in `onDraw`.

4. **Font loaded once:** `cjkTypeface` is loaded once in the companion object. All text `Paint` objects reference the same `Typeface` instance.

5. **Ring 6 label budget:** 60 labels × ~0.1 ms/label (CJK glyph) = ~6 ms. With GPU acceleration and glyph caching, this is within budget. Measured via `FrameMetrics` in performance test.

6. **Visibility skip:** If `ringVisible[i] == false`, the corresponding `drawRingNLabels()` call is skipped entirely.

### 10.2 Font Loading

```kotlin
// In LuopanFragment.onViewCreated:
LuopanView.initTypeface(requireContext())
```

`initTypeface` uses `ResourcesCompat.getFont()` which is synchronous on API 26+ for font resources bundled in `res/font/`. This is called once before the first `onDraw`, not inside `onDraw`.

### 10.3 Sector Lookup Performance

`SectorLookup` functions iterate over a small array (max 60 entries for Ring 6). The worst-case Ring 6 lookup is O(60) simple float comparisons. This is negligible (nanoseconds) compared to Canvas draw calls.

### 10.4 Battery (REQ-NFR-LUOPAN-03)

The sensor pipeline rate is unchanged from Phase 2. The additional draw work (6 rings vs. 1 compass rose) is offset by hardware acceleration. Battery measurement via Android battery historian in integration testing.

---

## 11. Error Handling

### 11.1 SENSOR_ERROR State

When `OverallConfidence.SENSOR_ERROR`:

- `LuopanStateMapper.map()` sets all character fields to `"—"`.
- `fenJinLabel = null`.
- `bearingDeg` is set to `compassState.last_valid_heading_deg?.toFloat() ?: 0f`.
- `LuopanView` still renders the dial at the last valid bearing (frozen rotation).
- "Lock 向" button: disabled (mapper sets `confidence = SENSOR_ERROR` in `LuopanState`).
- If lock is active: overlay remains visible with frozen bearing values.

### 11.2 STABILIZING State

When `OverallConfidence.STABILIZING`:

- All character fields continue updating from the live `bearingDeg` (gyroscope still provides heading).
- `fenJinLabel = null` → 分金 shows "N/A — calibrate for 分金 precision".
- "Lock 向" button: disabled.
- Dial: continues rotating.

### 11.3 Cold Start — First Bearing

When `LuopanFragment` opens before the first sensor frame arrives, `CompassUiState.INITIAL` is observed, which contains `LuopanState.INITIAL`. The dial renders at 0° with confidence `POOR`. As soon as the first valid `LuopanState` is emitted, the UI updates.

### 11.4 Font Load Failure

If `ResourcesCompat.getFont()` returns null (font file missing), `cjkTypeface` falls back to `Typeface.DEFAULT`. CJK characters may not render correctly but the app does not crash. This is logged as a warning.

### 11.5 Ring 6 Sector Gap

The 六十分金 ring has 60 sectors covering 360° (60 × 6° = 360°). If due to a data entry error a bearing falls in a gap, `SectorLookup.ring6()` throws `IllegalStateException`. This must not reach production. Defense: unit tests cover all 60 sector boundaries including wrap-around.

### 11.6 Navigation — Back Stack

Since the two fragments are top-level destinations (no child fragments), pressing back while in Luopan Mode navigates to the Android home screen (same behavior as Modern Mode). The `TabLayout` is always visible; no back-stack navigation between modes.

---

## 12. Test Strategy

### 12.1 Unit Tests — Domain Logic

**Package:** `com.luopan.compass.luopan`

#### 12.1.1 SectorLookupTest

**File:** `app/src/test/java/com/luopan/compass/luopan/SectorLookupTest.kt`

Covers all boundary assertions from REQ §5.3b and FSPEC Scenario H:

```kotlin
// Ring 4 — 子/亥 wrap-around
@Test fun ring4_344_9_is_hai() = assertEquals(11, SectorLookup.ring4(344.9f))   // 亥
@Test fun ring4_345_0_is_zi()  = assertEquals(0,  SectorLookup.ring4(345.0f))   // 子
@Test fun ring4_14_9_is_zi()   = assertEquals(0,  SectorLookup.ring4(14.9f))    // 子
@Test fun ring4_15_0_is_chou() = assertEquals(1,  SectorLookup.ring4(15.0f))    // 丑

// Ring 4 — generic boundary
@Test fun ring4_44_9_is_chou() = assertEquals(1,  SectorLookup.ring4(44.9f))    // 丑
@Test fun ring4_45_0_is_yin()  = assertEquals(2,  SectorLookup.ring4(45.0f))    // 寅

// Ring 5 — 子 wrap-around and sub-15° boundaries
@Test fun ring5_7_4_is_zi()    = assertEquals(1,  SectorLookup.ring5(7.4f))     // 子
@Test fun ring5_7_5_is_gui()   = assertEquals(2,  SectorLookup.ring5(7.5f))     // 癸
@Test fun ring5_22_4_is_gui()  = assertEquals(2,  SectorLookup.ring5(22.4f))    // 癸
@Test fun ring5_22_5_is_chou() = assertEquals(3,  SectorLookup.ring5(22.5f))    // 丑

// Ring 3 — 45° boundaries
@Test fun ring3_22_4_is_kan()  = assertEquals(0,  SectorLookup.ring3(22.4f))    // ☵ 坎 北
@Test fun ring3_22_5_is_gen()  = assertEquals(1,  SectorLookup.ring3(22.5f))    // ☶ 艮 東北
@Test fun ring3_67_4_is_gen()  = assertEquals(1,  SectorLookup.ring3(67.4f))    // ☶ 艮 東北
@Test fun ring3_67_5_is_zhen() = assertEquals(2,  SectorLookup.ring3(67.5f))    // ☳ 震 東

// Ring 6 — 壬子分金 wrap-around
@Test fun ring6_357_9_is_gengzi()  // 庚子分金
@Test fun ring6_358_0_is_renzi()   // 壬子分金
@Test fun ring6_0_0_is_renzi()     // 壬子分金
@Test fun ring6_4_0_is_jiazi()     // 甲子分金

// Key test bearings from REQ §5.6
@Test fun ring6_180_is_renwu()     // 壬午分金 (sector 178°–184°)
@Test fun ring6_0_is_renzi()       // 壬子分金 (sector 358°–4°)
@Test fun ring6_90_is_renmao()     // 壬卯分金 (sector 88°–94°)

// Normalization
@Test fun ring4_360_is_same_as_0()
@Test fun ring4_negative_normalized()
```

#### 12.1.2 LuopanStateMapperTest

**File:** `app/src/test/java/com/luopan/compass/luopan/LuopanStateMapperTest.kt`

Tests for all `OverallConfidence` states and representative bearings:

```kotlin
// HIGH confidence at 180° — verify all fields
@Test fun mapper_high_180_all_fields_correct()

// MODERATE — fenJinLabel is null
@Test fun mapper_moderate_fenjin_null()

// POOR — fenJinLabel is null; character fields still populated
@Test fun mapper_poor_characters_populated()

// STABILIZING — character fields populated; fenJinLabel null
@Test fun mapper_stabilizing_characters_populated_fenjin_null()

// SENSOR_ERROR — all character fields are "—"
@Test fun mapper_sensor_error_all_dashes()

// showMyLanguage = true — English labels used
@Test fun mapper_show_my_language_uses_english()

// showRomanization = false — pinyin fields empty
@Test fun mapper_romanization_off_pinyin_empty()
```

#### 12.1.3 ZuoXiangLockTest

**File:** `app/src/test/java/com/luopan/compass/luopan/ZuoXiangLockTest.kt`

```kotlin
// Wrap-around: 向 = 270° → 坐 = 90°
@Test fun lock_270_zuo_is_90()

// Wrap-around: 向 = 350° → 坐 = 170°
@Test fun lock_350_zuo_is_170()

// Zero: 向 = 0° → 坐 = 180°
@Test fun lock_0_zuo_is_180()

// 向 = 180° → 坐 = 0°
@Test fun lock_180_zuo_is_0()

// 向 = 45° → 坐 = 225°, xiangMountain = "艮", zuoMountain = "坤"
@Test fun lock_45_mountains_correct()

// 向 = 90° → 坐 = 270°, xiangMountain = "卯", zuoMountain = "酉"
@Test fun lock_90_mountains_correct()

// Clear removes lock state
@Test fun clear_resets_lock()

// Rederive updates 山 labels
@Test fun rederive_updates_mountains()
```

#### 12.1.4 RingLabelProviderTest

**File:** `app/src/test/java/com/luopan/compass/luopan/RingLabelProviderTest.kt`

```kotlin
// Ring 3 sector 4 (☲ 離 南) — verify character, pinyin, english
@Test fun ring3_sector4_is_li_south()

// Ring 4 sector 0 (子 Zǐ Rat) 
@Test fun ring4_sector0_is_zi()

// Ring 5 sector 13 (午 Wǔ Horse)
@Test fun ring5_sector13_is_wu()

// Ring 6 sector 31 (壬午分金)
@Test fun ring6_sector31_is_renwu()

// All 60 Ring 6 labels are non-empty
@Test fun ring6_all_labels_non_empty()
```

### 12.2 Unit Tests — SettingsRepository

**File:** `app/src/test/java/com/luopan/compass/settings/SettingsRepositoryTest.kt` (existing, extended)

```kotlin
@Test fun luopanShowRomanization_default_false()
@Test fun luopanShowMyLanguage_default_false()
@Test fun displayMode_default_MODERN()
@Test fun displayMode_persists_LUOPAN()
```

### 12.3 Robolectric Tests — LuopanView

**File:** `app/src/test/java/com/luopan/compass/ui/LuopanViewTest.kt`

Uses Robolectric (already in `build.gradle.kts`) to test View rendering math without an emulator:

```kotlin
// Rotation math: dialRotationDeg = -bearingDeg
@Test fun setHeading_90_rotates_minus_90()

// Zoom clamping
@Test fun setZoomScale_below_min_clamped_to_0_8()
@Test fun setZoomScale_above_max_clamped_to_2_0()

// Ring visibility — does not crash when all rings hidden
@Test fun all_rings_hidden_no_crash()
```

### 12.4 Integration Tests — Navigation and ViewModel Sharing

**File:** `app/src/androidTest/java/com/luopan/compass/ui/ModeSwitcherTest.kt` (new)

```kotlin
// Switching from Modern to Luopan completes within 300ms
@Test fun mode_switch_luopan_under_300ms()

// Lock state preserved across mode switch
@Test fun lock_preserved_across_mode_switch()

// Same ViewModel instance shared between fragments
@Test fun fragments_share_viewmodel_instance()
```

### 12.5 Instrumented Tests — LuopanFragment

**File:** `app/src/androidTest/java/com/luopan/compass/ui/LuopanFragmentTest.kt` (new)

```kotlin
// Lock button disabled when confidence POOR
@Test fun lock_button_disabled_at_poor_confidence()

// Lock button enabled when confidence HIGH
@Test fun lock_button_enabled_at_high_confidence()

// Ring visibility bottom sheet shown on long press
@Test fun long_press_shows_ring_visibility_sheet()

// 分金 field shows N/A at MODERATE confidence
@Test fun fen_jin_shows_na_at_moderate()
```

### 12.6 Test Doubles

| Double | Used In | Purpose |
|--------|---------|---------|
| `FakeCompassUiState` | `LuopanStateMapperTest` | Construct `CompassUiState` with specific confidence + bearing |
| `FakeLuopanView` | ViewModel tests | Capture `setBearingDeg` calls without Android |

---

## 13. Requirements Traceability

| REQ ID | Requirement | TSPEC Section | Implementation |
|--------|-------------|---------------|----------------|
| REQ-DISPLAY-04 | Luopan Mode overview | §6.1, §9 | `LuopanView`, `LuopanFragment`, nav graph |
| REQ-DISPLAY-05 | Numerical readout | §6.2, §5.1 | `LuopanFragment.updateNumericReadout()`, `LuopanState` |
| REQ-DISPLAY-06 | 坐向 lock | §4.4, §6.2, §8 | `ZuoXiangLock`, lock button wiring |
| REQ-LUOPAN-01 | 後天八卦 ring | §4.1, §4.2 | `SectorLookup.ring3()`, `RingLabelProvider.ring3Label()` |
| REQ-LUOPAN-02 | 十二地支 ring | §4.1, §4.2 | `SectorLookup.ring4()`, `RingLabelProvider.ring4Label()` |
| REQ-L10N-02 | Luopan terminology | §4.2, §4.3, §7 | `LabelData.english`, `showMyLanguage`, `showRomanization` |
| REQ-NFR-LUOPAN-01 | ≥20 fps dial rotation | §10 | Hardware-accelerated Canvas, pre-computed geometry |
| REQ-NFR-LUOPAN-02 | ≤16 ms onDraw | §10.1 | Pre-compute in `onSizeChanged`; no computation in `onDraw` |
| REQ-NFR-LUOPAN-03 | ≤7% battery/hr | §10.4 | Unchanged sensor rate; hardware-accelerated drawing |
| REQ-NFR-LUOPAN-04 | ≤300 ms mode entry | §9.4 | Dark background shown immediately; no spinner |
| Scenario H — all boundary cases | §5.3b | §12.1.1 | `SectorLookupTest` covers all boundary assertions |
| BR-05 — confidence → lock state | §2.3, §5.1 | §6.2, §8 | `lockXiang()` guards, `LuopanState.confidence` |
| BR-06 — 坐向 derivation | §4.4 | `ZuoXiangLock.lock()` | |
| BR-07 — 分金 display rule | §4.3 | `LuopanStateMapper` confidence gate | |
| BR-08 — default zh-Hant | §4.2, §7 | `LabelData.character` default; `showMyLanguage` opt-in | |
| BR-09 — session vs persisted | §7.1, §7.2 | Ring visibility + zoom in ViewModel; romanization in `SettingsRepository` | |
| BR-10 — lock across mode switch | §8.1 | `ZuoXiangLock` lives in `CompassViewModel` (scoped to Activity) | |
| Open Issue 1 — Float type | §2.1 | `LuopanState`: `xiangBearing: Float?`, `zuoBearing: Float?` | |
| Open Issue 2 — sign convention | §2.2 | Confirmed East-positive; View never re-applies declination | |
| Open Issue 3 — STABILIZING | §2.3, §11.2 | Dial rotates; sectors update; lock disabled; 分金 = N/A | |
| Open Issue 4 — `declinationDeg` in View | §2.4, §5.1 | `LuopanState.declinationDeg` field exposed; not used for conversion | |

---

*End of TSPEC-luopan-p3-luopan-mode*
