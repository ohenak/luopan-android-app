package com.luopan.compass.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

interface UrlLauncher {
    fun launch(url: String): Result

    sealed class Result {
        object Launched : Result()
        object NoBrowserFound : Result()
    }
}

class SystemUrlLauncher(private val context: Context) : UrlLauncher {
    override fun launch(url: String): UrlLauncher.Result {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            UrlLauncher.Result.Launched
        } catch (e: ActivityNotFoundException) {
            UrlLauncher.Result.NoBrowserFound
        }
    }
}
