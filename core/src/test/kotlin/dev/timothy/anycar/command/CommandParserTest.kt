package dev.timothy.anycar.command

import dev.timothy.anycar.command.Command.Call
import dev.timothy.anycar.command.Command.Navigate
import dev.timothy.anycar.command.Command.Text
import dev.timothy.anycar.command.Command.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private fun p(s: String) = CommandParser.parse(s)

    @Test fun help() = listOf("help", "What can you say?", "what can you do").forEach { assertEquals(Command.Help, p(it)) }

    @Test fun cancel() = listOf("cancel", "Never mind.", "nevermind", "forget it").forEach { assertEquals(Command.Cancel, p(it)) }

    @Test fun call() {
        assertEquals(Call("Mom"), p("call Mom"))
        assertEquals(Call("Mom"), p("Call Mom."))
        assertEquals(Call("Mom"), p("phone Mom"))
        assertEquals(Call("507 555 0143"), p("dial 507 555 0143"))
        assertEquals(Unknown("call"), p("call"))
    }

    @Test fun text() {
        assertEquals(Text("Mom", "I'm running late"), p("text Mom I'm running late"))
        assertEquals(Text("Mom", "I'm late"), p("send a text to Mom saying I'm late"))
        assertEquals(Text("Mom", "I'm late"), p("message Mom I'm late"))
        assertEquals(Text("Mom", "I'll be home at six."), p("Text Mom that I'll be home at six."))
        assertEquals(Text("Mom", ""), p("text Mom"))
        assertEquals(Text("5075550143", "here"), p("text 5075550143 here"))
    }

    @Test fun goHome() = listOf("go home", "Take me home.", "navigate home", "navigate to home").forEach { assertEquals(Command.GoHome, p(it)) }

    @Test fun navigate() {
        assertEquals(Navigate("Walmart Sioux Falls"), p("navigate to Walmart Sioux Falls"))
        assertEquals(Navigate("O'Brien's Pub & Grill"), p("Navigate to O'Brien's Pub & Grill."))
        assertEquals(Navigate("Casey's"), p("take me to Casey's"))
        assertEquals(Navigate(""), p("navigate"))
        assertEquals(Navigate(""), p("navigate to"))
    }

    @Test fun media() {
        listOf("play", "Pause.", "resume", "play music", "pause music", "resume music").forEach { assertEquals(Command.MediaToggle, p(it)) }
        listOf("next", "skip", "next song", "skip song").forEach { assertEquals(Command.MediaNext, p(it)) }
        listOf("previous", "back", "previous song", "go back").forEach { assertEquals(Command.MediaPrevious, p(it)) }
    }

    @Test fun weatherTimeDiagnose() {
        listOf("weather", "What's the weather?", "whats the weather", "how’s the weather", "is it raining", "what's the weather tomorrow")
            .forEach { assertEquals(it, Command.Weather, p(it)) }
        listOf("time", "What time is it?", "what's the time", "current time").forEach { assertEquals(Command.Time, p(it)) }
        listOf("check engine", "check engine light", "read codes", "diagnostics", "What's that light?")
            .forEach { assertEquals(Command.Diagnose, p(it)) }
    }

    @Test fun mustNotMatch() {
        listOf(
            "callback", "dialog", "textbook", "texting while driving", "messages", "go home screen", "stop",
            "weather channel", "timer", "what time does Walmart open", "navigation settings",
        ).forEach { assertEquals(it, Unknown(it), p(it)) }
    }

    @Test fun callMeHarmlesslyMatches() = assertEquals(Call("me"), p("call me"))

    @Test fun chain() {
        assertEquals(
            listOf(Text("Mom", "I'm late"), Navigate("Walmart"), Command.MediaNext),
            CommandParser.parseChain("Text Mom I'm late, then navigate to Walmart and skip"),
        )
        assertEquals(listOf(Call("Dad"), Command.Time), CommandParser.parseChain("call Dad and then what time is it"))
    }

    @Test fun notAChain() {
        assertNull(CommandParser.parseChain("text Mom pick up bread and milk"))
        assertNull(CommandParser.parseChain("navigate to Barnes and Noble"))
        assertNull(CommandParser.parseChain("call Mom"))
    }

    @Test fun phoneNumbers() {
        assertEquals("5075550143", CommandParser.phoneDigits("507-555-0143"))
        assertEquals("5075550143", CommandParser.phoneDigits("(507) 555.0143"))
        assertEquals("+15075550143", CommandParser.phoneDigits("+1 507 555 0143"))
        assertNull(CommandParser.phoneDigits("Mom"))
        assertEquals("911", CommandParser.phoneDigits("911"))
        assertNull(CommandParser.phoneDigits("12345"))
        assertEquals("507 555 0143", CommandParser.spokenDigits("5075550143"))
    }
}
