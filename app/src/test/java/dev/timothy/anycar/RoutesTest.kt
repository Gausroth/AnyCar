package dev.timothy.anycar

import dev.timothy.anycar.ui.Routes
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutesTest {
    @Test fun routesAreDistinct() = assertNotEquals(Routes.HOME, Routes.DEBUG)
}
