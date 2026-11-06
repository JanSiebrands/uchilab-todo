package de.uchilab.todo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.uchilab.todo.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** The name comes from the resources of the running configuration, so a changed title keeps the tests green. */
    private fun assertAppNameDisplayed() {
        val appName = composeRule.activity.getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun `app starts and shows the app name`() {
        assertAppNameDisplayed()
    }

    @Test
    @Config(qualifiers = "de-night")
    fun `start works with german locale and dark mode`() {
        assertAppNameDisplayed()
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `start works with english locale and light mode`() {
        assertAppNameDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `start works with a locale without translation`() {
        assertAppNameDisplayed()
    }
}
