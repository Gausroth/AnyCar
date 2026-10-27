package dev.timothy.anycar.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.timothy.anycar.ui.screens.CarScreen
import dev.timothy.anycar.ui.theme.CarScreenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarScreenTest {
    @get:Rule val compose = createComposeRule()

    /** M4.6 test 4: car mode has no text entry anywhere. */
    @Test fun noTextFieldsInCarMode() {
        compose.setContent { CarScreenTheme { CarScreen(onQuit = {}) } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun showsTheBottomRow() {
        compose.setContent { CarScreenTheme { CarScreen(onQuit = {}) } }
        compose.onNodeWithText("More").assertIsDisplayed()
        compose.onNodeWithText("Quit car mode").assertIsDisplayed()
        compose.onAllNodesWithText("Systems", substring = true).assertCountEquals(1)
    }
}
