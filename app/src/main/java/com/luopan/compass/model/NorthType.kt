package com.luopan.compass.model

/**
 * The north reference used for heading display and bearing capture.
 *
 * In Phase 2 the UI toggle offers exactly two states: [MAGNETIC] and [TRUE].
 * [GRID] is defined in the enum to support the [BearingCaptureUseCase] guard
 * (AT-G-08) but it is never reachable from the UI toggle in Phase 2.
 *
 * TSPEC §5.6: NorthType lives in com.luopan.compass.model to avoid circular
 * package dependencies.
 */
enum class NorthType { MAGNETIC, TRUE, GRID }
