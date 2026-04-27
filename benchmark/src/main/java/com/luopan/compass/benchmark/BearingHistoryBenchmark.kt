package com.luopan.compass.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring fling frame timing on the Bearing History tab with 500 seeded records.
 *
 * NFR: p50 frame duration must be ≤ 16 ms (60 fps) when scrolling the bearing history list
 * at maximum velocity with 500 records (PLAN G-1 / TSPEC Phase 4 performance requirements).
 *
 * DEVICE-ONLY: This test targets the `:app` module under the `benchmark` build type and must
 * be run on a physical device (or emulator with EMULATOR error suppression) via:
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * CI gate: NON-BLOCKING — failures emit a regression alert but do not block merges.
 * The `suppressErrors` runner argument already includes EMULATOR and LOW-BATTERY.
 *
 * Setup requirement: The app must have ≥ 500 bearing records in its database before this
 * benchmark runs. In a CI environment this is achieved by running the seed script or
 * by pre-populating the app's shared preferences / database via ADB before the benchmark.
 *
 * Note: Because the :benchmark module uses the `android.test` plugin (self-instrumenting),
 * it drives the `:app` package from outside. The benchmark navigates to the History tab
 * by clicking the third tab item identified by its content description.
 */
@RunWith(AndroidJUnit4::class)
class BearingHistoryBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Fling benchmark — measures frame timing while flinging the bearing history RecyclerView
     * at SWIPE_DOWN direction for [FLING_ITERATIONS] swipes.
     *
     * Pass condition (advisory / non-blocking): p50 frame duration ≤ 16 ms (60 fps target).
     * If the p50 exceeds 16 ms the benchmark reports the result but does not fail CI.
     */
    @Test
    fun bearingHistoryFling_frameTimingWithin16ms() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = StartupMode.WARM,
            iterations = BENCHMARK_ITERATIONS
        ) {
            // 1. Wait for the app to be fully displayed
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), APP_WAIT_TIMEOUT_MS)

            // 2. Navigate to the Bearing History tab (third tab, index 2)
            //    The tab has contentDescription "History" set in activity_compass.xml.
            //    Fall back to position-based click if the content description is not found.
            val historyTab = device.findObject(By.desc(HISTORY_TAB_CONTENT_DESC))
            historyTab?.click()
                ?: device.findObject(By.res(TARGET_PACKAGE, "tab_history"))?.click()

            // 3. Wait for the RecyclerView to appear
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, RECYCLER_VIEW_ID)),
                LIST_WAIT_TIMEOUT_MS
            )

            // 4. Fling the list to generate frame timing data
            val recyclerView = device.findObject(By.res(TARGET_PACKAGE, RECYCLER_VIEW_ID))
            repeat(FLING_ITERATIONS) {
                recyclerView?.fling(Direction.DOWN, FLING_SPEED_PPS)
                recyclerView?.fling(Direction.UP, FLING_SPEED_PPS)
            }
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.luopan.compass"

        /**
         * Content description of the History tab item.
         * Must match android:contentDescription in the TabItem definition inside activity_compass.xml.
         */
        private const val HISTORY_TAB_CONTENT_DESC = "History"

        /**
         * Resource ID of the bearing history RecyclerView.
         * Defined in fragment_bearing_history.xml as `android:id="@+id/recycler_history"`.
         */
        private const val RECYCLER_VIEW_ID = "recycler_history"

        /**
         * Number of benchmark iterations. 5 is the standard CI value (matches StartupBenchmark).
         */
        private const val BENCHMARK_ITERATIONS = 5

        /**
         * Number of fling cycles (down + up) per iteration.
         * 3 cycles gives enough frames to compute reliable p50/p90 frame timing statistics.
         */
        private const val FLING_ITERATIONS = 3

        /**
         * Fling speed in pixels per second. 15 000 px/s represents a fast fling that exercises
         * the RecyclerView's item recycling under load.
         */
        private const val FLING_SPEED_PPS = 15_000

        /** Maximum wait for app foreground after startupMode. */
        private const val APP_WAIT_TIMEOUT_MS = 5_000L

        /** Maximum wait for RecyclerView to become visible after tab navigation. */
        private const val LIST_WAIT_TIMEOUT_MS = 3_000L
    }
}
