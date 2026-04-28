package com.luopan.compass.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.luopan.compass.R
import com.luopan.compass.calibration.ui.CalibrationWizardActivity
import com.luopan.compass.db.DatabaseKeyManager
import com.luopan.compass.db.LuopanDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Third tab in CompassActivity. Displays the bearing history list with:
 * - Search bar (hidden in State A: zero records)
 * - Age-based and drift-based recalibration banners
 * - RecyclerView with BearingAdapter (expand/collapse, swipe-to-delete)
 * - Empty states (zero records / no search results)
 */
class BearingHistoryFragment : Fragment() {

    companion object {
        /**
         * Duration in milliseconds for the undo Snackbar after swipe-to-delete.
         * Exactly 5 seconds — NOT Snackbar.LENGTH_LONG (which is 2750 ms).
         * PROP-HIST-042: this named constant locks down the user-observable contract.
         */
        const val UNDO_SNACKBAR_DURATION_MS = 5000
    }

    private val compassViewModel: CompassViewModel by activityViewModels()

    private val historyViewModel: BearingHistoryViewModel by viewModels {
        val keyManager = DatabaseKeyManager(requireActivity().application)
        val db = LuopanDatabase.getInstance(requireActivity().application, keyManager.getOrCreatePassphrase())
        object : AbstractSavedStateViewModelFactory(this@BearingHistoryFragment, arguments) {
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                @Suppress("UNCHECKED_CAST")
                return BearingHistoryViewModel(dao = db.bearingDao(), savedStateHandle = handle) as T
            }
        }
    }

    private lateinit var adapter: BearingAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: SearchView
    private lateinit var emptyNoRecords: View  // LinearLayout (illustration + text) for State A
    private lateinit var emptyNoResults: TextView
    private lateinit var ageBannerRoot: View
    private lateinit var ageBannerText: TextView
    private lateinit var ageBannerClose: ImageButton
    private lateinit var driftBannerRoot: View
    private lateinit var driftBannerText: TextView
    private lateinit var driftBannerClose: ImageButton

    private var currentSnackbar: Snackbar? = null

    // Two separate launchers — one per banner path (SE FSPEC-v2 F-03 resolution)
    private lateinit var calWizardLauncherAge: ActivityResultLauncher<Intent>
    private lateinit var calWizardLauncherDrift: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Both launchers registered in onCreate() per Android Jetpack best practice
        // (must be registered before onStart())
        calWizardLauncherAge = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                compassViewModel.onCalibrationCompleteFromHistory()
            }
        }

        calWizardLauncherDrift = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                compassViewModel.resetDriftDetector()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_bearing_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        searchBar = view.findViewById(R.id.search_bar)
        recyclerView = view.findViewById(R.id.recycler_history)
        emptyNoRecords = view.findViewById(R.id.empty_state_no_records)
        emptyNoResults = view.findViewById(R.id.empty_state_no_results)
        ageBannerRoot = view.findViewById(R.id.banner_age_root)
        ageBannerText = view.findViewById(R.id.banner_age_text)
        ageBannerClose = view.findViewById(R.id.banner_age_close)
        driftBannerRoot = view.findViewById(R.id.banner_drift_root)
        driftBannerText = view.findViewById(R.id.banner_drift_text)
        driftBannerClose = view.findViewById(R.id.banner_drift_close)

        // Set up RecyclerView and adapter
        adapter = BearingAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Set up swipe-to-delete
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_ID.toInt()) return
                val record = adapter.currentList[position]

                // Commit any prior undo before starting a new one
                currentSnackbar?.dismiss()
                historyViewModel.deleteRecord(record)

                currentSnackbar = Snackbar.make(requireView(), R.string.bearing_deleted, UNDO_SNACKBAR_DURATION_MS)
                    .setAction(R.string.undo) {
                        historyViewModel.undoDelete()
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(snackbar: Snackbar, event: Int) {
                            if (event != DISMISS_EVENT_ACTION) {
                                historyViewModel.commitDelete()
                            }
                        }
                    })
                currentSnackbar?.show()
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)

        // Search bar text changes
        searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                historyViewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        // Age banner close button
        ageBannerClose.setOnClickListener {
            compassViewModel.dismissCalAgeBanner()
            ageBannerRoot.visibility = View.GONE
        }

        // Drift banner close button
        driftBannerClose.setOnClickListener {
            compassViewModel.dismissDriftBanner()
            driftBannerRoot.visibility = View.GONE
        }

        // Age banner body tap → launch calibration wizard
        ageBannerRoot.setOnClickListener {
            calWizardLauncherAge.launch(Intent(requireContext(), CalibrationWizardActivity::class.java))
        }

        // Drift banner body tap → launch calibration wizard
        driftBannerRoot.setOnClickListener {
            calWizardLauncherDrift.launch(Intent(requireContext(), CalibrationWizardActivity::class.java))
        }

        // Observe bearing list + search query
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    historyViewModel.bearingList.collectLatest { records ->
                        val query = historyViewModel.searchQuery.value
                        updateListState(records, query)
                    }
                }

                // Observe CompassViewModel age banner state
                launch {
                    compassViewModel.uiState.collect { state ->
                        updateAgeBanner(state.calibration_age_days)
                    }
                }

                // Observe drift banner state
                launch {
                    compassViewModel.driftBannerState.collect { bannerState ->
                        updateDriftBanner(bannerState)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh calibration age on fragment become-visible
        compassViewModel.loadCalibrationAge()
    }

    private fun updateListState(records: List<com.luopan.compass.bearing.BearingRecord>, query: String) {
        val isEmpty = records.isEmpty()
        val isSearchActive = query.isNotEmpty()

        when {
            isEmpty && !isSearchActive -> {
                // State A: zero records, no search
                searchBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyNoRecords.visibility = View.VISIBLE
                emptyNoResults.visibility = View.GONE
            }
            isEmpty && isSearchActive -> {
                // No search results
                searchBar.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyNoRecords.visibility = View.GONE
                emptyNoResults.visibility = View.VISIBLE
            }
            else -> {
                // Normal list (>=1 record)
                searchBar.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
                emptyNoRecords.visibility = View.GONE
                emptyNoResults.visibility = View.GONE
                adapter.submitList(records)
            }
        }
    }

    private fun updateAgeBanner(ageDays: Long) {
        val showBanner = ageDays > 30L && !compassViewModel.calAgeBannerDismissed
        ageBannerRoot.visibility = if (showBanner) View.VISIBLE else View.GONE
        if (showBanner) {
            ageBannerText.text = getString(R.string.banner_cal_age, ageDays)
        }
    }

    private fun updateDriftBanner(bannerState: DriftBannerState) {
        driftBannerRoot.visibility = if (bannerState == DriftBannerState.VISIBLE) View.VISIBLE else View.GONE
        if (bannerState == DriftBannerState.VISIBLE) {
            driftBannerText.text = getString(R.string.banner_drift)
        }
    }
}
