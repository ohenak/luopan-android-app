package com.luopan.compass.bearing

/**
 * Port (minimal interface) for executing a bearing capture.
 *
 * [BearingCaptureUseCase] is the production implementation. In tests,
 * [com.luopan.compass.bearing.FakeBearingCaptureUseCase] implements this interface to
 * enable controllable suspension and assertion of [CompassViewModel.captureButtonEnabled]
 * state (TSPEC §10.1 TE-T-04 (c), BR-CAP-08).
 */
fun interface BearingCapturePort {
    /**
     * Executes the capture save operation.
     *
     * @param snapshot Immutable snapshot taken at capture button tap time.
     * @return The saved [BearingRecord].
     */
    suspend fun execute(snapshot: BearingSnapshot): BearingRecord
}
