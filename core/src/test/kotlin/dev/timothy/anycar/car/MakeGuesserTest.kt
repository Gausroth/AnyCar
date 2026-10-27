package dev.timothy.anycar.car

import org.junit.Assert.assertEquals
import org.junit.Test

class MakeGuesserTest {
    @Test fun syncIsFord() = assertEquals("Ford", MakeGuesser.guess("SYNC"))
    @Test fun makeNameInsideTheName() = assertEquals("Toyota", MakeGuesser.guess("My toyota Camry"))
    @Test fun wholeWordsOnly() = assertEquals("", MakeGuesser.guess("Ramona's phone"))
    @Test fun noMatchIsBlank() = assertEquals("", MakeGuesser.guess("CAR MULTIMEDIA"))
}
