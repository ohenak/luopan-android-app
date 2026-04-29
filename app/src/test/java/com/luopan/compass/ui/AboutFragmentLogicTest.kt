package com.luopan.compass.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.luopan.compass.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutFragmentLogicTest {

    @Test
    fun websiteUrl_isYijiStudio() {
        assertEquals("https://yiji.studio", AboutFragment.WEBSITE_URL)
    }

    @Test
    fun systemUrlLauncher_parsesUri_correctly() {
        val uri = android.net.Uri.parse(AboutFragment.WEBSITE_URL)
        assertEquals("https", uri.scheme)
        assertEquals("yiji.studio", uri.host)
    }

    @Test
    fun noBrowser_showsSnackbar() {
        val fake = FakeUrlLauncher().apply { result = UrlLauncher.Result.NoBrowserFound }
        val scenario = launchFragmentInContainer<AboutFragment>(
            themeResId = R.style.Theme_LuopanCompass
        )
        scenario.onFragment { fragment -> fragment.urlLauncher = fake }
        onView(withId(R.id.tv_about_website)).perform(click())
        onView(withText(R.string.about_no_browser_error)).check(matches(isDisplayed()))
        assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)
    }

    @Test
    fun launched_doesNotShowSnackbar() {
        val fake = FakeUrlLauncher().apply { result = UrlLauncher.Result.Launched }
        val scenario = launchFragmentInContainer<AboutFragment>(
            themeResId = R.style.Theme_LuopanCompass
        )
        scenario.onFragment { fragment -> fragment.urlLauncher = fake }
        onView(withId(R.id.tv_about_website)).perform(click())
        onView(withText(R.string.about_no_browser_error)).check(doesNotExist())
    }
}
