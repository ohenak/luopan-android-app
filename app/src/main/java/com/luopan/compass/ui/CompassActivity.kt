package com.luopan.compass.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.tabs.TabLayout
import com.luopan.compass.R
import com.luopan.compass.location.LocationRepository
import com.luopan.compass.magnetic.AndroidGeoFieldModel
import com.luopan.compass.magnetic.MagneticFieldModelProvider
import com.luopan.compass.magnetic.Wmm2025Model
import com.luopan.compass.model.NorthType
import com.luopan.compass.settings.SettingsRepository
import com.luopan.compass.util.WallClock

/**
 * Single Activity host for the Luopan compass app.
 *
 * Task 3.1 migration: CompassActivity is now a thin host that:
 * 1. Hosts a [NavHostFragment] + bottom [TabLayout] (see activity_compass.xml)
 * 2. Wires [TabLayout] ↔ [NavController] for mode navigation (TSPEC §9.3)
 * 3. Restores the last-used display mode from [SettingsRepository] on cold start (TSPEC §8.4)
 * 4. Manages the wake lock for screen-on behaviour
 *
 * All compass UI logic (permission flow, calibration wizard, bearing capture,
 * declination info sheet, etc.) has been migrated to [ModernCompassFragment].
 *
 * [CompassViewModel] is scoped to this Activity so both [ModernCompassFragment] and
 * [LuopanFragment] share the same ViewModel instance and sensor pipeline (TSPEC §1.3).
 */
class CompassActivity : AppCompatActivity() {

    private val clock = WallClock()

    val viewModel: CompassViewModel by viewModels {
        val locationPrefs = getSharedPreferences("location_cache", MODE_PRIVATE)
        val locationRepository = LocationRepository(locationPrefs, clock)
        val wmm = try { Wmm2025Model.fromContext(this, clock) } catch (e: Exception) { null }
        val fallback = AndroidGeoFieldModel(clock)
        val modelProvider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)
        CompassViewModel.Factory(application, modelProvider, locationRepository, clock)
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var navController: NavController

    private val wakeLockManager by lazy { WakeLockManager(this) }

    // Track whether we are programmatically selecting a tab (to avoid re-entrant navigation).
    private var isProgrammaticTabSelect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)

        tabLayout = findViewById(R.id.tabLayout)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Wire TabLayout ↔ NavController (TSPEC §9.3)
        wireTabNavigation()

        // Cold-start mode restoration (TSPEC §8.4):
        // If savedInstanceState is null, this is a fresh start — navigate to the persisted mode.
        if (savedInstanceState == null) {
            restoreDisplayModeOnColdStart()
        }
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wakeLockManager.acquire()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wakeLockManager.release()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.northType.value == NorthType.TRUE) {
            viewModel.checkWmmExpiry()
        }
    }

    // -----------------------------------------------------------------------
    // Tab ↔ NavController wiring (TSPEC §9.3)
    // -----------------------------------------------------------------------

    private fun wireTabNavigation() {
        // Tab selection → NavController.navigate
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isProgrammaticTabSelect) return
                when (tab.position) {
                    TAB_MODERN -> navController.navigate(R.id.dest_modern)
                    TAB_LUOPAN -> navController.navigate(R.id.dest_luopan)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // NavController destination changes → sync TabLayout selection
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val targetPosition = when (destination.id) {
                R.id.dest_modern -> TAB_MODERN
                R.id.dest_luopan -> TAB_LUOPAN
                else -> return@addOnDestinationChangedListener
            }
            val currentTab = tabLayout.getTabAt(targetPosition)
            if (tabLayout.selectedTabPosition != targetPosition && currentTab != null) {
                isProgrammaticTabSelect = true
                tabLayout.selectTab(currentTab)
                isProgrammaticTabSelect = false
            }
        }
    }

    // -----------------------------------------------------------------------
    // Cold-start mode restoration (TSPEC §8.4)
    // -----------------------------------------------------------------------

    private fun restoreDisplayModeOnColdStart() {
        val settings = SettingsRepository(this)
        if (settings.displayMode == SettingsRepository.DISPLAY_MODE_LUOPAN) {
            navController.navigate(R.id.dest_luopan)
        }
        // If MODERN (default), startDestination is already dest_modern — no navigation needed.
    }

    companion object {
        private const val TAB_MODERN = 0
        private const val TAB_LUOPAN = 1
    }
}
