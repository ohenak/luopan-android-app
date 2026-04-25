package com.luopan.compass.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.xmlpull.v1.XmlPullParser

class NoInternetPermissionCheck : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android", "name"
        )
        if (name == "android.permission.INTERNET") {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "INTERNET permission must not be declared (REQ-NFR-05)"
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "NoInternetPermission",
            briefDescription = "INTERNET permission must not be declared",
            explanation = "The Luopan compass app must not request INTERNET permission per REQ-NFR-05.",
            category = Category.SECURITY,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoInternetPermissionCheck::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
