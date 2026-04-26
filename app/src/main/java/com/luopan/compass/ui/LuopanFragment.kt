package com.luopan.compass.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.luopan.compass.R
import com.luopan.compass.settings.SettingsRepository

/**
 * Stub fragment for Luopan Mode — placeholder for Task 3.2 implementation.
 *
 * This fragment occupies the nav destination `dest_luopan`. The full Luopan UI
 * (six-ring dial, numerical readout panel, 坐向 lock) is implemented in Task 3.2.
 *
 * Responsibilities now (Task 3.1 scope):
 * - Inflate a placeholder layout with the Luopan background (#2C0E0E)
 * - Obtain the shared CompassViewModel via activityViewModels()
 * - Call viewModel.setDisplayMode(LUOPAN) in onStart so the last-used mode is persisted
 */
class LuopanFragment : Fragment() {

    private val viewModel: CompassViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_luopan_stub, container, false)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setDisplayMode(SettingsRepository.DISPLAY_MODE_LUOPAN)
    }
}
