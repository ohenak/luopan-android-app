package com.luopan.compass.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.luopan.compass.R

class AboutFragment : Fragment() {

    internal lateinit var urlLauncher: UrlLauncher

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (!::urlLauncher.isInitialized) {
            urlLauncher = SystemUrlLauncher(context)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tv_about_website).setOnClickListener {
            val result = urlLauncher.launch(WEBSITE_URL)
            if (result is UrlLauncher.Result.NoBrowserFound) {
                Snackbar.make(requireView(), R.string.about_no_browser_error, Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    companion object {
        const val WEBSITE_URL = "https://yiji.studio"
    }
}
