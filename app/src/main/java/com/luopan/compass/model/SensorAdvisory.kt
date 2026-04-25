package com.luopan.compass.model

/**
 * Sensor advisory states surfaced in [CompassUiState].
 *
 * - [NONE]: no advisory
 * - [NO_GYROSCOPE]: device lacks a gyroscope — accuracy reduced
 * - [POWER_SAVING]: OS power-saving mode reduces sensor sample rate
 * - [STABILIZING]: sensor pipeline is warming up
 * - [EXTREME_LATITUDE]: WMM inclination abs >= 80° — compass accuracy severely degraded near poles
 */
enum class SensorAdvisory {
    NONE,
    NO_GYROSCOPE,
    POWER_SAVING,
    STABILIZING,
    /** P7.1: WMM inclination abs >= 80° (near magnetic poles) — caps OverallConfidence at MODERATE. */
    EXTREME_LATITUDE
}
