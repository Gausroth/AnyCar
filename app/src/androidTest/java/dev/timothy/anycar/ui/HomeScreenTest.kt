package dev.timothy.anycar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.timothy.anycar.data.VehicleProfile
import dev.timothy.anycar.ui.screens.HomeScreen
import dev.timothy.anycar.ui.theme.CarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsAppNameAndDefaultCar() {
        val taurus = VehicleProfile(id = 1, btMac = "AA:BB:CC:DD:EE:FF", btName = "SYNC", make = "Ford", model = "Taurus", year = 2009, isDefaultVehicle = true)
        compose.setContent {
            CarTheme { HomeScreen(vehicles = listOf(taurus), onEnterCarMode = {}, onAddCar = {}, onOpenDebug = null) }
        }
        compose.onNodeWithText("AnyCar").assertIsDisplayed()
        compose.onNodeWithText("Enter car mode · 2009 Ford Taurus").assertIsDisplayed()
    }
}
