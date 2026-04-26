package com.luopan.compass.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring startup performance for the Luopan Compass app.
 *
 * REQ-NFR-08 targets:
 *   - Cold start: median ≤ 5 000 ms to first heading display
 *   - Warm start: median ≤ 3 000 ms to first heading display
 *
 * Run on a physical device with the :benchmark build variant to get representative results.
 * On an emulator, results will be suppressed via the EMULATOR error suppression argument.
 *
 * Invoke via:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Cold start benchmark — measures time from process creation to first heading value
     * displayed on screen (REQ-NFR-08, TSPEC §10.4 CompassColdStartBenchmark).
     *
     * Uses CompilationMode.Full() to simulate an optimised APK (post-install AOT),
     * representing the steady-state experience for end users after the first launch.
     *
     * Success criterion: median startup time ≤ 5 000 ms over 5 iterations.
     */
    @Test
    fun coldStart() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = StartupMode.COLD,
            iterations = BENCHMARK_ITERATIONS
        ) {
            pressHome()
            startActivityAndWait()
            waitForFirstHeading()
        }
    }

    /**
     * Warm start benchmark — measures time from process resume to first heading value
     * displayed on screen (REQ-NFR-08, TSPEC §10.4 CompassWarmStartBenchmark).
     *
     * Uses CompilationMode.None() so the JIT warm-up cost is included in the measurement,
     * representing a realistic warm restart scenario (e.g., user switching back to the app).
     *
     * Success criterion: median startup time ≤ 3 000 ms over 5 iterations.
     */
    @Test
    fun warmStart() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = BENCHMARK_ITERATIONS
        ) {
            pressHome()
            startActivityAndWait()
            waitForFirstHeading()
        }
    }

    /**
     * Waits until the heading display (compass bearing text view) is visible on screen.
     * The content description "compass heading" is set on the heading TextView in
     * activity_compass.xml, allowing UiAutomator to locate it without relying on
     * implementation-specific resource IDs.
     *
     * Timeout of 10 000 ms exceeds both the 5 s cold-start and 3 s warm-start targets,
     * ensuring the wait itself does not inflate measurement times.
     */
    private fun MacrobenchmarkScope.waitForFirstHeading() {
        device.wait(
            Until.hasObject(By.desc(HEADING_CONTENT_DESCRIPTION)),
            HEADING_WAIT_TIMEOUT_MS
        )
    }

    companion object {
        private const val TARGET_PACKAGE = "com.luopan.compass"

        /** Number of iterations for CI benchmarking. 5 is the standard CI value. */
        private const val BENCHMARK_ITERATIONS = 5

        /**
         * Content description for the heading display view.
         * Must match android:contentDescription in activity_compass.xml.
         */
        private const val HEADING_CONTENT_DESCRIPTION = "compass heading"

        /**
         * Maximum wait time for the first heading to appear.
         * 10 000 ms is deliberately larger than both performance budgets (5 s cold, 3 s warm)
         * so a slow start does not terminate the wait before the metric is recorded.
         */
        private const val HEADING_WAIT_TIMEOUT_MS = 10_000L
    }
}
