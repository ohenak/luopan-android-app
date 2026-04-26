package com.luopan.compass.luopan

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the 坐向 (Zuo-Xiang) lock state for a Luopan consultation session.
 *
 * Thread-safety contract (TE-F04):
 *   - [lock], [clear], and [rederive] are always called from the ViewModel's `viewModelScope`
 *     — single-writer guarantee.
 *   - [lockState] is read-only and may be called from any thread; [AtomicReference] provides
 *     safe publication without additional synchronisation (volatile semantics).
 *
 * Lock state lifecycle:
 *   - Fresh instance: [lockState] is `null` (unlocked).
 *   - After [lock]: [lockState] is a fully-populated [LockState] with all fields non-null.
 *   - After [clear]: [lockState] is `null` again.
 *   - After [rederive]: only [LockState.displayXiangBearing] and [LockState.displayZuoBearing]
 *     change; the stored True North [LockState.xiangBearing] and [LockState.zuoBearing] are
 *     invariant to north-type switches (FSPEC §4d, AC-23).
 *
 * No Android dependencies — safe to use in pure JVM unit tests.
 */
class ZuoXiangLock {

    /**
     * Immutable snapshot of the 坐向 lock state.
     *
     * All bearing fields use [Float] (TSPEC §2.1).
     *
     * @param xiangBearing       向 bearing in True North degrees — NEVER changes after lock.
     * @param zuoBearing         坐 bearing = (xiangBearing + 180) % 360 — NEVER changes after lock.
     * @param xiangMountain      Ring 5 山 character for [xiangBearing], e.g. "艮".
     * @param zuoMountain        Ring 5 山 character for [zuoBearing], e.g. "坤".
     * @param displayXiangBearing Display-reference-adjusted 向 bearing (may differ from
     *                            [xiangBearing] when Magnetic North is active); updated by [rederive].
     * @param displayZuoBearing   Display-reference-adjusted 坐 bearing; updated by [rederive].
     */
    data class LockState(
        val xiangBearing: Float,
        val zuoBearing: Float,
        val xiangMountain: String,
        val zuoMountain: String,
        val displayXiangBearing: Float,
        val displayZuoBearing: Float
    )

    /**
     * Backing store. `null` when unlocked; non-null when locked.
     * [AtomicReference] volatile semantics provide safe publication across coroutine dispatchers.
     */
    private val _lockState = AtomicReference<LockState?>(null)

    /**
     * Current lock state. `null` when not locked.
     *
     * May be read from any thread — [AtomicReference] provides safe publication.
     */
    val lockState: LockState? get() = _lockState.get()

    /**
     * Locks 向 at the given True North bearing.
     *
     * Derives:
     *  - `zuoBearing = (xiangBearing + 180) % 360`
     *  - `xiangMountain` and `zuoMountain` from Ring 5 LUT via [SectorLookup.ring5] and
     *    [RingLabelProvider.ring5Label]
     *  - Display bearings are initialised to the True North values; call [rederive] afterwards
     *    if the current display is showing Magnetic North.
     *
     * @param bearing True North bearing in degrees. The caller (ViewModel [lockXiang]) is
     *                responsible for converting from Magnetic North to True North before calling
     *                this method (FSPEC §4a step 3).
     *
     * Always called from ViewModel scope — single-writer guarantee.
     */
    fun lock(bearing: Float) {
        val xiang = ((bearing % 360f) + 360f) % 360f
        val zuo = (xiang + 180f) % 360f
        val xiangMtn = RingLabelProvider.ring5Label(SectorLookup.ring5(xiang)).character
        val zuoMtn = RingLabelProvider.ring5Label(SectorLookup.ring5(zuo)).character
        _lockState.set(
            LockState(
                xiangBearing = xiang,
                zuoBearing = zuo,
                xiangMountain = xiangMtn,
                zuoMountain = zuoMtn,
                displayXiangBearing = xiang,  // initialised to True N; rederive() adjusts for Mag N
                displayZuoBearing = zuo
            )
        )
    }

    /**
     * Clears the lock. [lockState] returns `null` after this call.
     *
     * Always called from ViewModel scope — single-writer guarantee.
     */
    fun clear() {
        _lockState.set(null)
    }

    /**
     * Recalculates display bearings based on the current north reference and declination.
     *
     * The stored True North bearings ([LockState.xiangBearing] and [LockState.zuoBearing])
     * are NEVER modified by this method (FSPEC §4d). Only [LockState.displayXiangBearing]
     * and [LockState.displayZuoBearing] change.
     *
     * Mountain labels ([LockState.xiangMountain], [LockState.zuoMountain]) are also unchanged —
     * they are derived from True North and are invariant to north-type switches (AC-23).
     *
     * This method does NOT call [lock]; doing so would overwrite [LockState.xiangBearing].
     *
     * If the lock is not active ([lockState] is `null`), this method is a no-op.
     *
     * @param declinationDeg East-positive declination in degrees. A positive value means
     *                       magnetic north is east of True North (same sign convention as
     *                       [CompassUiState.declination_deg] / TSPEC §2.2).
     * @param isMagneticNorth `true` when the user's display is currently showing Magnetic North.
     *
     * Always called from ViewModel scope — single-writer guarantee.
     */
    fun rederive(declinationDeg: Float, isMagneticNorth: Boolean) {
        val current = _lockState.get() ?: return
        val displayXiang = if (isMagneticNorth) {
            ((current.xiangBearing - declinationDeg + 360f) % 360f)
        } else {
            current.xiangBearing
        }
        val displayZuo = if (isMagneticNorth) {
            ((current.zuoBearing - declinationDeg + 360f) % 360f)
        } else {
            current.zuoBearing
        }
        // Preserve all stored True North values and mountain labels — only display fields change.
        _lockState.set(
            current.copy(
                displayXiangBearing = displayXiang,
                displayZuoBearing = displayZuo
            )
        )
    }
}
