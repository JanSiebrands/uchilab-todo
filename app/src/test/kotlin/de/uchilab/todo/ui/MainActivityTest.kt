package de.uchilab.todo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app starts and shows the working title`() {
        composeRule.onNodeWithText("uchilab-todo").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-night")
    fun `start works with german locale and dark mode`() {
        composeRule.onNodeWithText("uchilab-todo").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-notnight")
    fun `start works with english locale and light mode`() {
        composeRule.onNodeWithText("uchilab-todo").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `start works with a locale without translation`() {
        composeRule.onNodeWithText("uchilab-todo").assertIsDisplayed()
    }
}
