package com.luopan.compass.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Issue

class LuopanIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(NoInternetPermissionCheck.ISSUE)
    override val api: Int = com.android.tools.lint.detector.api.CURRENT_API
    override val vendor: Vendor = Vendor(
        vendorName = "Luopan",
        feedbackUrl = "https://github.com/ohenak/luopan-android-app/issues"
    )
}
