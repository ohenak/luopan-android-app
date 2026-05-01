package com.luopan.compass.ui

internal class FakeUrlLauncher : UrlLauncher {
    var result: UrlLauncher.Result = UrlLauncher.Result.Launched
    var lastUrl: String? = null

    override fun launch(url: String): UrlLauncher.Result {
        lastUrl = url
        return result
    }
}
